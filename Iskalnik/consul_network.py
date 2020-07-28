import networkx as nx
from pyvis.network import Network

from database import network_connections

consul_graph = nx.Graph()
for conn in network_connections('CONSUL'):
    consul_graph.add_edge(conn[1], conn[2])

consul_network = Network(height="100%", width="70%")
consul_network.from_nx(consul_graph)

consul_network.show_buttons(filter_=['physics'])
consul_network.show("network_outputs/consul.html")