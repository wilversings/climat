FROM azul/zulu-openjdk:21-latest

RUN apt update
RUN apt -y install npm curl

WORKDIR /mnt/climat

RUN npm -g install n
RUN n latest
