package com.vmware.vhadoop.vhm.model.scenarios;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.logging.Logger;

import com.vmware.vhadoop.vhm.model.api.Allocation;
import com.vmware.vhadoop.vhm.model.vcenter.VirtualCenter;


public class FaultInjectionSerengeti extends Serengeti {
   private static Logger _log = Logger.getLogger(FaultInjectionSerengeti.class.getName());

   public FaultInjectionSerengeti(String id, VirtualCenter vCenter) {
      super(id, vCenter);
   }

   /**
    * Hides the template defined in Serengeti
    */
   public static class MasterTemplate extends Master.Template {
      @Override
      public FaultInjectionMaster instantiate(VirtualCenter vCenter, String id, Allocation capacity, Serengeti serengeti) {
         return new FaultInjectionMaster(vCenter, id, capacity, serengeti);
      }
   }

   static public class FaultInjectionMaster extends Master {
      Queue<Integer> recommissionFailures = new LinkedList<Integer>();
      Queue<Integer> decommissionFailures = new LinkedList<Integer>();
      Queue<Integer> expectedFailureMessages = new LinkedList<Integer>();

      Map<Integer,String> expectedResponse = new HashMap<Integer,String>();

      FaultInjectionMaster(VirtualCenter vCenter, String id, Allocation capacity, Serengeti serengeti) {
         super(vCenter, id, capacity, serengeti);
      }

      @Override
      public synchronized String enable(String hostname) {
         if (!recommissionFailures.isEmpty()) {
            Integer injectedError = recommissionFailures.poll();
            _log.info(name()+": injecting enable failure: "+injectedError);
            expectedFailureMessages.add(injectedError);
            return injectedError.toString();
         } else {
            return super.enable(hostname);
         }
      }

      @Override
      public synchronized String disable(String hostname) {
         if (!decommissionFailures.isEmpty()) {
            Integer injectedError = decommissionFailures.poll();
            _log.info(name()+": injecting disable failure: "+injectedError);
            expectedFailureMessages.add(injectedError);
            return injectedError.toString();
         } else {
            return super.disable(hostname);
         }
      }

      public synchronized void queueRecommissionFailure(Integer errorCode) {
         recommissionFailures.add(errorCode);
      }

      public synchronized void queueDecommissionFailure(Integer errorCode) {
         decommissionFailures.add(errorCode);
      }
   }
}
