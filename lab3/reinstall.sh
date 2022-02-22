tmppp=$(pwd)
cd bridge-app

mvn clean install -DskipTests
onos localhost app deactivate "nctu.winlab.bridge"
onos-app localhost uninstall "nctu.winlab.bridge"
onos-app localhost install! target/bridge-app-1.0-SNAPSHOT.oar

cd $tmppp