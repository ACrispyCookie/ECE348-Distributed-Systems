make -C server/ clean-all
rm -rf client/src/__pycache__ client/src/*.txt client/tests/*.txt client/tests/__pycache__

mkdir -p G5_1
cp -r client/ G5_1
cp -r server/ G5_1
cp -r README.md G5_1
tar -zcvf G5_1.tar.gz G5_1
rm -rf G5_1 