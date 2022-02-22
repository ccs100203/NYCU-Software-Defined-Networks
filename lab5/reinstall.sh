tmppp=$(pwd)
cd ProxyArp

mvn clean install -DskipTests
onos localhost app deactivate "nctu.winlab.ProxyArp"
onos-app localhost uninstall "nctu.winlab.ProxyArp"
onos-app localhost install! target/ProxyArp-1.0-SNAPSHOT.oar

cd $tmppp