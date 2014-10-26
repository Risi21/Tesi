mvn clean package
cd target
mkdir export
cp /home/luca/Scaricati/jr2.war ./export/jackrabbit.war
sudo jar uf CloneCluster-1.0-SNAPSHOT.one-jar.jar export/jackrabbit.war
