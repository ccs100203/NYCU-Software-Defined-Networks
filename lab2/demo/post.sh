appid='test'


#### Version 3

for d in `find . -name "*.json"`
do
    switch=${d:9:1}
    curl -u onos:rocks \
    -X POST \
    -H 'Content-Type: application/json' \
    -d @${d} \
    -i \
    "http://localhost:8181/onos/v1/flows/of:000000000000000${switch}?appId=$appid"
done
