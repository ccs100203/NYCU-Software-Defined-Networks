sudo mn -c

# sudo mn --controller=remote,127.0.0.1:6653

# sudo mn --topo=linear,3 --controller=remote,127.0.0.1:6653

sudo mn --custom=topo_310551100.py --topo=topo_310551100 \
--controller=remote,ip=127.0.0.1,port=6653

sudo mn -c
