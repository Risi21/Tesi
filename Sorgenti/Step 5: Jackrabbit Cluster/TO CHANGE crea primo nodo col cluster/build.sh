mvn clean package
cd target
mkdir export
cp /home/luca/Scaricati/jr2.war ./export/jackrabbit.war
sudo jar uf Step7-1.0-SNAPSHOT.jar export/jackrabbit.war