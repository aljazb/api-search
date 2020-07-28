import networkx as nx
import matplotlib.pyplot as plt
from pyvis.network import Network

from database import network_connections

metrics_network = Network(height="100%", width="70%")

metrics_graph = nx.Graph()
for conn in network_connections('METRICS'):
    metrics_network.add_node(conn[1])
    metrics_network.add_node(conn[2])
    metrics_network.add_edge(conn[1], conn[2], value=conn[4])

# metrics_network.from_nx(metrics_graph)

# nx.draw_networkx(metrics_graph, arrows=True, node_size=3, with_labels=False)
# plt.show()

metrics_network.show_buttons(filter_=['physics'])
metrics_network.show("network_outputs/metrics.html")