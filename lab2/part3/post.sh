appid='test'

# if [ -z $1 ] ; then
# echo 'p for post, d for delete'
# exit 1

# elif [ $1 = 'p' ] ; then
# echo post

# elif [ $1 = 'd' ] ; then
# echo delete

# else
# echo wtf
# exit 1
# fi

### Add rules

# max=2
# for i in `seq 1 $max`
# do
#     curl -u onos:rocks \
#     -X POST \
#     -H 'Content-Type: application/json' \
#     -d @flows_s${i}-1_310551100.json \
#     -i \
#     "http://localhost:8181/onos/v1/flows/of:0000000000000001?appId=$appid"
# done

#### Version 2

# find . -name "*.json" | xargs -I % -n1 \
# curl -u onos:rocks \
#     -X POST \
#     -H 'Content-Type: application/json' \
#     -d @% \
#     -i \
#     "http://localhost:8181/onos/v1/flows/of:0000000000000001?appId=$appid"


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
