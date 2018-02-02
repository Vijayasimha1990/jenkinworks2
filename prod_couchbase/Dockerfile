FROM couchbase:community-4.5.1

COPY configure-node.sh /opt/couchbase

RUN chmod -R 777 /opt/couchbase

CMD ["/opt/couchbase/configure-node.sh"]