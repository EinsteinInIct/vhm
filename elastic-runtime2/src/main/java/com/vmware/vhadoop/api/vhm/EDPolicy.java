package com.vmware.vhadoop.api.vhm;

import java.util.*;

public interface EDPolicy {
   void enableTTs(Set<String> toEnable, int totalTargetEnabled, String clusterId, ClusterMap cluster) throws Exception;

   /* Caller should expect this to block */
   void disableTTs(Set<String> toDisable, int totalTargetEnabled, String clusterId, ClusterMap cluster) throws Exception;
}
