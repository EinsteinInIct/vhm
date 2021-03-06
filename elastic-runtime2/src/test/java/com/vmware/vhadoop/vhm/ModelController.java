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

import java.util.logging.Logger;

import com.vmware.vhadoop.api.vhm.HadoopActions;
import com.vmware.vhadoop.api.vhm.VCActions;
import com.vmware.vhadoop.util.ThreadLocalCompoundStatus;
import com.vmware.vhadoop.vhm.hadoop.ModelHadoopAdaptor;
import com.vmware.vhadoop.vhm.model.scenarios.Serengeti;
import com.vmware.vhadoop.vhm.rabbit.ModelRabbitAdaptor;
import com.vmware.vhadoop.vhm.vc.ModelVcAdapter;

public class ModelController extends BootstrapMain {
   private static Logger _log = Logger.getLogger(ModelController.class.getName());

   Serengeti vApp;

   public ModelController(Serengeti serengeti) {
      this.vApp = serengeti;
   }

   public ModelController(final String configDir, final String logFileName, Serengeti serengeti) {
      super(configDir, null, logFileName);
      this.vApp = serengeti;
   }

   @Override
   public VCActions getVCInterface(ThreadLocalCompoundStatus tlcs) {
      return new ModelVcAdapter(vApp.getVCenter());
   }

   @Override
   HadoopActions getHadoopInterface(ThreadLocalCompoundStatus tlcs) {
      return new ModelHadoopAdaptor(vApp.getVCenter(), tlcs);
   }

   @Override
   ModelRabbitAdaptor getRabbitInterface() {
      return new ModelRabbitAdaptor(vApp.getVCenter());
   }
}
