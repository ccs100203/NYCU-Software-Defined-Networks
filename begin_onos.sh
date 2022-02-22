tmpppp=$(pwd)
cd $ONOS_ROOT
echo "go to $ONOS_ROOT"

bazel run onos-local -- clean debug

cd $tmpppp
echo "go to $tmpppp"

