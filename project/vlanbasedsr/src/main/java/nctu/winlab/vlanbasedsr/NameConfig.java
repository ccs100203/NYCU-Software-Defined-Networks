/*
 * Copyright 2020-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nctu.winlab.vlanbasedsr;

import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;
import java.util.List;

// "SwitchNum": "3",
// "DeviceId": ["of:0000000000000001", "of:0000000000000002", "of:0000000000000003"],
// "IsEdge": ["0", "1", "1"],
// "VlanId": ["101", "102", "103"],
// "Subnet": ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]

public class NameConfig extends Config<ApplicationId> {

  public static final String SwitchNum = "SwitchNum";
  public static final String DeviceId = "DeviceId";
  public static final String IsEdge = "IsEdge";
  public static final String VlanId = "VlanId";
  public static final String Subnet = "Subnet";

  @Override
  public boolean isValid() {
    // return hasOnlyFields(NAME);
    return true;
  }

  public int switchnum() {
    return Integer.parseInt(get(SwitchNum, null));
  }

  public List<String> deviceid() {
    return getList(DeviceId, (String s1) -> { return s1;});
  }

  public List<Boolean> isedge() {
    return getList(IsEdge, (String s1) -> { return s1.equals("1");});
  }

  public List<String> vlanid() {
    return getList(VlanId, (String s1) -> { return s1;});
  }

  public List<String> subnet() {
    return getList(Subnet, (String s1) -> { return s1;});
  }
}

