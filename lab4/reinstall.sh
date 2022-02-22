tmppp=$(pwd)
cd unicastdhcp

mvn clean install -DskipTests
onos localhost app deactivate "nctu.winlab.unicastdhcp"
onos-app localhost uninstall "nctu.winlab.unicastdhcp"
onos-app localhost install! target/unicastdhcp-1.0-SNAPSHOT.oar

cd $tmppp