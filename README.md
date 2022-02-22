# NYCU-Software-Defined-Networks

The SDN-NFV homework at NYCU from fall 2021 to spring 2022.

## Commands & Configs


### Activate ONOS Server
```
source begin_onos.sh
```


### Connect ONOS
```
onos localhost
```


### ONOS CLI
POST to create new rule
GET to get information
DELETE to delete current rule
- template
deviceID: of:0000000000000001
appId: test
```
curl -u onos:rocks \
-X POST \
-H 'Content-Type: application/json' \
-d @flows1.json \
-i \
'http://localhost:8181/onos/v1/flows/of:0000000000000001?appId=test'
```

- upload config to onos
```
onos-netcfg localhost config.json
```


### ONOS GUI
- URL
http://localhost:8181/onos/ui
User/Password: onos/rocks

- DOC
http://localhost:8181/onos/v1/docs

We can create / delete flow rules in **flows**.


### Activate default mininet
```
source activate_mn.sh
```


### Mininet Command
- clear arp table
```
h1 arp -e
h1 arp -d h2
```

- 
