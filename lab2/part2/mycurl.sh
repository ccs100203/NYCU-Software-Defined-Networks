appid='test'

### Add rules

# max=4
# for i in `seq 1 $max`
# do
#     curl -u onos:rocks \
#     -X POST \
#     -H 'Content-Type: application/json' \
#     -d @flows_s1-${i}_310551100.json \
#     -i \
#     "http://localhost:8181/onos/v1/flows/of:0000000000000001?appId=$appid"
# done

### Delete rules by appid

curl -u onos:rocks \
-X DELETE \
--header 'Accept: application/json' \
-i \
"http://localhost:8181/onos/v1/flows/application/$appid"
