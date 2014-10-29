#cancella le librerie open di java
cd /usr/lib/jvm
sudo rm -r *

#installa java 1.8 oracle
sudo add-apt-repository ppa:webupd8team/java
sudo apt-get -y update
sudo apt-get -y install oracle-java8-installer
