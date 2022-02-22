### Delete rules by appid

appid='test'

curl -u onos:rocks \
-X DELETE \
--header 'Accept: application/json' \
-i \
"http://localhost:8181/onos/v1/flows/application/$appid"
