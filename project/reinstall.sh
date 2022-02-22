tmppp=$(pwd)
cd vlanbasedsr

mvn clean install -DskipTests
onos localhost app deactivate "nctu.winlab.vlanbasedsr"
onos-app localhost uninstall "nctu.winlab.vlanbasedsr"
onos-app localhost install! target/vlanbasedsr-1.0-SNAPSHOT.oar

cd $tmppp