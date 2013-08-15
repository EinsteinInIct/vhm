/***************************************************************************
* Copyright (c) 2013 VMware, Inc. All Rights Reserved.
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
***************************************************************************/

package com.vmware.vhadoop.vhm.strategy;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import com.vmware.vhadoop.api.vhm.ClusterMap;
import com.vmware.vhadoop.api.vhm.HadoopActions;
import com.vmware.vhadoop.api.vhm.HadoopActions.HadoopClusterInfo;
import com.vmware.vhadoop.api.vhm.VCActions;
import com.vmware.vhadoop.api.vhm.strategy.EDPolicy;
import com.vmware.vhadoop.util.CompoundStatus;
import com.vmware.vhadoop.util.LogFormatter;
import com.vmware.vhadoop.util.VhmLevel;
import com.vmware.vhadoop.vhm.AbstractClusterMapReader;

public class JobTrackerEDPolicy extends AbstractClusterMapReader implements EDPolicy {
   private static final Logger _log = Logger.getLogger(JobTrackerEDPolicy.class.getName());

   private final HadoopActions _hadoopActions;
   private final VCActions _vcActions;

   private static final long MAX_DNS_WAIT_TIME_MILLIS = 120000;
   private static final long DNS_WAIT_SLEEP_TIME_MILLIS = 5000;
   
   public JobTrackerEDPolicy(HadoopActions hadoopActions, VCActions vcActions) {
      _hadoopActions = hadoopActions;
      _vcActions = vcActions;
   }

   /* This method blocks until it has made all reasonable efforts to determine that the TTs have been successfully registered with the JT 
    * 
    * When VMs are powered down, the DNS name and IP address are wiped to ensure that no stale entries persist. As such, recommission gets
    * a list of ttVmIds for powered-off VMs, none of which will yet have a DNS name. Typically VC gets the update of a fresh DNS name after
    * the JobTracker, so once the DNS names have come through from VC, the checkTargetTTsSuccess should complete as a formality.
    */
   @Override
   public Set<String> enableTTs(Set<String> ttVmIds, int totalTargetEnabled, String clusterId) throws Exception {
      HadoopClusterInfo hadoopCluster = null;
      Set<String> activeVmIds = null;
 
      ClusterMap clusterMap = null;
      try {
         clusterMap = getAndReadLockClusterMap();
         hadoopCluster = clusterMap.getHadoopInfoForCluster(clusterId);
      } finally {
         unlockClusterMap(clusterMap);
      }

      if ((hadoopCluster != null) && (hadoopCluster.getJobTrackerIpAddr() != null)) {
         CompoundStatus status = getCompoundStatus();

         _log.log(VhmLevel.USER, "<%C"+clusterId+"%C>"+constructUserLogMessage(ttVmIds, null, false));

         /* pass ttVMids in here for now - this is currently bogus but harmless - all this does currently is delete any exclude list */
         _hadoopActions.recommissionTTs(ttVmIds, hadoopCluster);
         
         if (_vcActions.changeVMPowerState(ttVmIds, true) == null) {
            status.registerTaskFailed(false, "Failed to change VM power state in VC");
            _log.log(VhmLevel.USER, "<%C"+clusterId+"%C> Failed to power on Task Trackers");
         } else {
            if (status.screenStatusesForSpecificFailures(new String[]{VCActions.VC_POWER_ON_STATUS_KEY})) {
               Set<String> newDnsNames = blockAndGetDnsNamesForVmIdsWithoutCachedDns(ttVmIds, MAX_DNS_WAIT_TIME_MILLIS);
               if (newDnsNames != null) {
                  Set<String> activeDnsNames = _hadoopActions.checkTargetTTsSuccess("Recommission", newDnsNames, totalTargetEnabled, hadoopCluster);
                  activeVmIds = getActiveVmIds(activeDnsNames);
               }
            } else {
               _log.log(VhmLevel.USER, "<%C"+clusterId+"%C> Unexpected VC error powering on Task Trackers");
            }
         }
      }
      return activeVmIds;
   }

   /* This method blocks until it has made all reasonable efforts to determine that the TTs have been successfully unregistered with the JT 
    * 
    * Effective hadoop de-commission must work with dnsNames, whereas the power-off needs VM IDs. In certain error cases, there may be no
    * valid DNS name for some of the vmIds to de-commission. In this case, we must simply power those off. Note that this may leave the JT
    * thinking that these TTs are still alive for a period of time */
   @Override
   public Set<String> disableTTs(Set<String> ttVmIds, int totalTargetEnabled, String clusterId) throws Exception {
      Map<String, String> dnsNameMap = null;
      HadoopClusterInfo hadoopCluster = null;
      Set<String> successfulIds = null;

      ClusterMap clusterMap = null;
      try {
         clusterMap = getAndReadLockClusterMap();
         hadoopCluster = clusterMap.getHadoopInfoForCluster(clusterId);
         dnsNameMap = clusterMap.getDnsNamesForVMs(ttVmIds);
      } finally {
         unlockClusterMap(clusterMap);
      }

      if ((dnsNameMap != null) && (hadoopCluster != null) && (hadoopCluster.getJobTrackerIpAddr() != null)) {
         CompoundStatus status = getCompoundStatus();
         
         Set<String> validDnsNames = getValidDnsNames(dnsNameMap);
         Set<String> vmIdsWithInvalidDns = getVmIdsWithInvalidDnsNames(dnsNameMap);
         /* Since we can only check for de-commission of VMs with valid dns names, we should adjust the target accordingly */
         int newTargetEnabled = (vmIdsWithInvalidDns == null) ? totalTargetEnabled : totalTargetEnabled + vmIdsWithInvalidDns.size();

         _log.log(VhmLevel.USER, "<%C"+clusterId+"%C>"+constructUserLogMessage(vmIdsWithInvalidDns, validDnsNames, true));

         /* Only send TTs with valid dnsNames to be properly decommissioned - the rest will just be powered off */
         if (validDnsNames != null) {
            _hadoopActions.decommissionTTs(validDnsNames, hadoopCluster);
         }

         if (status.screenStatusesForSpecificFailures(new String[]{"decomRecomTTs"})) {
            Set<String> activeDnsNames = _hadoopActions.checkTargetTTsSuccess("Decommission", validDnsNames, newTargetEnabled, hadoopCluster);
            successfulIds = getVmIdSubset(ttVmIds, getActiveVmIds(activeDnsNames));
            Set<String> unsuccessfulIds = getVmIdSubset(ttVmIds, successfulIds);
            if (!unsuccessfulIds.isEmpty()) {
               _log.log(VhmLevel.USER, "The following task trackers failed to decommission cleanly: "+LogFormatter.constructListOfLoggableVms(unsuccessfulIds));
            }
         }
         /* Power off all the VMs, decommissioned or not - note this does not block */
         if (_vcActions.changeVMPowerState(ttVmIds, false) == null) {
            status.registerTaskFailed(false, "Failed to change VM power state in VC");
            _log.log(VhmLevel.USER, "<%C"+clusterId+"%C> Unexpected VC error powering off Task Trackers");
         }
      }
      return successfulIds;
   }
   
   private Set<String> getVmIdsWithInvalidDnsNames(Map<String, String> dnsNameMap) {
      Set<String> vmIdsWithInvalidDnsNames = null;
      for (String ttVmId : dnsNameMap.keySet()) {
         String dnsName = dnsNameMap.get(ttVmId);
         if ((dnsName == null) || (dnsName.trim().length() == 0)) {
            if (vmIdsWithInvalidDnsNames == null) {
               vmIdsWithInvalidDnsNames = new HashSet<String>();
            }
            vmIdsWithInvalidDnsNames.add(ttVmId);
         }
      }
      return vmIdsWithInvalidDnsNames;
   }

   private Set<String> blockAndGetDnsNamesForVmIdsWithoutCachedDns(Set<String> vmIdsWithInvalidDns, long timeoutMillis) {
      long endTime = System.currentTimeMillis() + timeoutMillis;
      Set<String> result = null;
      if (vmIdsWithInvalidDns != null) {
         do {
            ClusterMap clusterMap = null;
            Map<String, String> newDnsNameMap = null;
            try {
               clusterMap = getAndReadLockClusterMap();
               newDnsNameMap = clusterMap.getDnsNamesForVMs(vmIdsWithInvalidDns);
               if (newDnsNameMap == null) {
                  return null;         /* This would mean that our vmIds themselves have become invalid, which would only occur if vms are deleted */
               }
               result = new HashSet<String>(newDnsNameMap.values());
               if (!result.contains(null) && !result.contains("")) {
                  _log.info("Found valid DNS names for all VMs");
                  return result;
               }
            } finally {
               unlockClusterMap(clusterMap);
            }
            _log.info("Looking for valid DNS names for "+LogFormatter.constructListOfLoggableVms(getVmIdsWithInvalidDnsNames(newDnsNameMap)));
            try {
               Thread.sleep(DNS_WAIT_SLEEP_TIME_MILLIS);
            } catch (InterruptedException e) {}
         } while (System.currentTimeMillis() <= endTime);
         /* If we fell out of the loop, it's likely we didn't find everything we were looking for */
         if (result != null) {
            result.remove(null);
            result.remove("");
            if (result.isEmpty()) {
               result = null;
            }
         }
      }
      return result;
   }

   private String constructUserLogMessage(Set<String> vmIdsWithInvalidDns, Set<String> validDnsNames, boolean isDecommission) {
      int toDeRecommission = (validDnsNames == null) ? 0 : (validDnsNames.size());
      int toChangePowerState = (vmIdsWithInvalidDns == null) ? 0 : (vmIdsWithInvalidDns.size());
      String powerOffMsg = (toChangePowerState == 0) ? "" : " powering "+(isDecommission ? "off " : "on ")+toChangePowerState+" task tracker"+
                                                         (toChangePowerState > 1 ? "s" : "");
      String decommissionMsg = (toDeRecommission == 0) ? "" : " "+(isDecommission ? "de" : "re")+"commissioning "+toDeRecommission+" task tracker"+
                                                         (toDeRecommission > 1 ? "s" : "") + (toChangePowerState > 0 ? ";" : "");
      return decommissionMsg + powerOffMsg;
   }

   private Set<String> getActiveVmIds(Set<String> activeDnsNames) {
      if (activeDnsNames == null) {
         return null;
      }
      ClusterMap clusterMap = null;
      Set<String> result = null;
      try {
         clusterMap = getAndReadLockClusterMap();
         Map<String, String> dnsNameMap = clusterMap.getVmIdsForDnsNames(activeDnsNames);
         if (dnsNameMap != null) {
            result = new HashSet<String>(dnsNameMap.values());
         }
      } finally {
         unlockClusterMap(clusterMap);
      }
      return result;
   }

   private Set<String> getValidDnsNames(Map<String, String> hostNames) {
      Set<String> result = null;
      for (String dnsName : hostNames.values()) {
         if ((dnsName != null) && (dnsName.trim().length() > 0)) {
            if (result == null) {
               result = new HashSet<String>();
            }
            result.add(dnsName);
         }
      }
      return result;
   }

   private Set<String> getVmIdSubset(Set<String> allVmIds, Set<String> toRemove) {
      Set<String> result = new HashSet<String>(allVmIds);
      if (toRemove != null) {
         result.removeAll(toRemove);
      }
      return result;
   }

   @Override
   public Set<String> getActiveTTs(String clusterId) throws Exception {
      HadoopClusterInfo hadoopCluster = null;

      ClusterMap clusterMap = null;
      try {
         clusterMap = getAndReadLockClusterMap();
         hadoopCluster = clusterMap.getHadoopInfoForCluster(clusterId);
      } finally {
         unlockClusterMap(clusterMap);
      }

      if ((hadoopCluster != null) && (hadoopCluster.getJobTrackerIpAddr() != null)) {
         return _hadoopActions.getActiveTTs(hadoopCluster, 0);
      }
      return null;
   }

}
