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

package com.vmware.vhadoop.vhm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Ignore;
import org.junit.Test;

import com.vmware.vhadoop.vhm.hadoop.HadoopErrorCodes;
import com.vmware.vhadoop.vhm.model.scenarios.FaultInjectionSerengeti.FaultInjectionMaster;
import com.vmware.vhadoop.vhm.rabbit.VHMJsonReturnMessage;

public class SerengetiFaultTest extends AbstractFaultInjectionSerengetiTestBase {
   /**
    * Tests diagnostic messages in recommission cases
    */
   @Ignore // refactoring of serengeti into appliance/application has broken these and they're not yet fixed
   @Test
   public void testBasicRecommissionError() {
      final int numberOfHosts = 2;
      final int computeNodesPerHost = 2;
      String clusterName = "faultInjectionRecommission";

      /* general test setup */
      setup(numberOfHosts);

      /* create a cluster to work with */
      FaultInjectionMaster cluster = createCluster(clusterName, computeNodesPerHost);

      /* set the general timeout for the faults */
      setTimeout((computeNodesPerHost * LIMIT_CYCLE_TIME) + TEST_WARM_UP_TIME);

      /* queue faults */
      Integer fault = HadoopErrorCodes.UNKNOWN_ERROR;
      cluster.queueRecommissionFailure(fault);

      /* elicit faults */
      String id = cluster.setTargetComputeNodeNum(2);
      assertMessageResponse("waiting for report of injected failure ", cluster, id);

      VHMJsonReturnMessage response = cluster.waitForResponse(id, 0);
      assertNotNull("Expected detail message in response to recommission failure", response.error_msg);
      assertTrue("Expected recommission error message to start with known error description", response.error_msg.startsWith("Unknown exit status during recommission"));

      assertVMsInPowerState("expected VMs to power on regardless", cluster, 2, true);
      assertEquals("expected one compute node to be enabled", 1, cluster.numberComputeNodesInState(true));
   }

   /**
    * Tests diagnostic messages in recommission cases
    */
   @Ignore // refactoring of serengeti into appliance/application has broken these and they're not yet fixed
   @Test
   public void testBasicErrorDecommissionError() {
      final int numberOfHosts = 2;
      final int computeNodesPerHost = 2;
      String clusterName = "faultInjectionDecommission";

      /* general test setup */
      setup(numberOfHosts);

      /* create a cluster to work with */
      FaultInjectionMaster cluster = createCluster(clusterName, computeNodesPerHost);

      /* set the general timeout for the faults */
      setTimeout((computeNodesPerHost * LIMIT_CYCLE_TIME) + TEST_WARM_UP_TIME);

      String msgid = cluster.setTargetComputeNodeNum(2);
      assertVMsInPowerState("expected VMs to power on regardless", cluster, 2, true);
      assertMessageResponse("waiting for target compute nodes to succeed", cluster, msgid);

      /* queue faults */
      Integer fault = HadoopErrorCodes.UNKNOWN_ERROR;
      cluster.queueDecommissionFailure(fault);


      /* elicit faults */
      String id = cluster.setTargetComputeNodeNum(0);
      assertMessageResponse("waiting for report of injected failure ", cluster, id);

      VHMJsonReturnMessage response = cluster.waitForResponse(id, 0);
      assertNotNull("Expected detail message in response to decommission failure", response.error_msg);
      assertTrue("Expected decommission error message to start with known error description", response.error_msg.startsWith("Unknown exit status during decommission"));

      assertVMsInPowerState("expected VMs to power off regardless", cluster, 0, true);
      assertEquals("expected one compute node to still be enabled", 1, cluster.numberComputeNodesInState(true));
   }

   /**
    * Tests diagnostic messages in recommission cases
    */
   @Ignore // refactoring of serengeti into appliance/application has broken these and they're not yet fixed
   @Test
   public void testInterleavedSuccessError() {
      final int numberOfHosts = 2;
      final int computeNodesPerHost = 3;
      String clusterName = "faultInjectionIntermittent";

      /* general test setup */
      setup(numberOfHosts);

      /* create a cluster to work with */
      FaultInjectionMaster cluster = createCluster(clusterName, computeNodesPerHost);

      /* set the general timeout for the faults */
      setTimeout((computeNodesPerHost * LIMIT_CYCLE_TIME) + TEST_WARM_UP_TIME);

      for (int i = 0; i < 6; i++) {
         boolean inject = i % 2 != 0;
         if (inject) {
            /* queue faults */
            Integer fault = HadoopErrorCodes.UNKNOWN_ERROR;
            cluster.queueRecommissionFailure(fault);
         }

         String id = cluster.setTargetComputeNodeNum(i);
         assertMessageResponse("waiting for report of injected failure ", cluster, id);

         VHMJsonReturnMessage response = cluster.waitForResponse(id, 0);
         if (inject) {
            assertNotNull("Expected detail message in response to decommission failure", response.error_msg);
            assertTrue("Expected decommission error message to start with known error description", response.error_msg.startsWith("Unknown exit status during recommission"));
         }

         assertVMsInPowerState("expected VMs to power on regardless", cluster, i, true);
         assertEquals("expected compute node to fail enabling", i/2, cluster.numberComputeNodesInState(true));
      }
   }

   /**
    * Tests diagnostic messages with unknown errors
    */
   @Ignore
   @Test
   public void testUnknownError() {
      final int numberOfHosts = 1;
      final int computeNodesPerHost = 1;
      String clusterName = "faultInjectionUnknownError";

      int UNRECOGNIZED_ERROR = -41;

      /* general test setup */
      setup(numberOfHosts);

      /* create a cluster to work with */
      FaultInjectionMaster cluster = createCluster(clusterName, computeNodesPerHost);

      /* set the general timeout for the faults */
      setTimeout((computeNodesPerHost * LIMIT_CYCLE_TIME) + TEST_WARM_UP_TIME);

      /* queue faults */
      Integer fault = UNRECOGNIZED_ERROR;
      cluster.queueRecommissionFailure(fault);

      /* elicit faults */
      String id = cluster.setTargetComputeNodeNum(1);
      assertMessageResponse("waiting for report of injected failure ", cluster, id);

      VHMJsonReturnMessage response = cluster.waitForResponse(id, 0);
      assertNotNull("Expected detail message in response to decommission failure", response.error_msg);
      assertTrue("Expected decommission error message to start with known error description", response.error_msg.startsWith("Unknown exit status during recommission"));

      assertVMsInPowerState("expected VMs to power on regardless", cluster, 2, true);
      assertEquals("expected one compute node to be enabled", 1, cluster.numberComputeNodesInState(true));
   }
}
