import networkx as nx
import matplotlib.pyplot as plt
from pyvis.network import Network
import community

from colors import get_different_colors
from database import network_connections, service_info


def partition_git_network():
    git_network = nx.Graph()
    for conn in network_connections('GIT'):
        if conn[4] > 0.0:
            git_network.add_edge(conn[1], conn[2], weight=conn[4])
    partition = community.community_louvain.best_partition(git_network, random_state=1)

    # transforms to map with partition id as key and list of tables as value
    partitions_services = {}
    for key, value in sorted(partition.items()):
        partitions_services.setdefault(value, []).append(key)
    return partition, partitions_services


def get_service_community_neighbours(service, partition, partition_services):
    neighbours = set()
    if service in partition:
        neighbours = partition_services[partition[service]]
        neighbours.remove(service)
    return neighbours


services = service_info()

git_partition, git_partitions_services = partition_git_network()
partition_count = len(set(git_partition.values()))
colors = get_different_colors(partition_count+1)
vis_network = Network(height="100%", width="70%")
for s in services:
    vis_network.add_node(s['name'], color=colors[git_partition.get(s['name'], partition_count)])
for conn in network_connections('GIT'):
    if conn[4] > 0.3:
        vis_network.add_edge(conn[1], conn[2], value=conn[4])
# nx.draw_networkx(git_graph, arrows=True, node_size=3, with_labels=False)
# plt.show()

# nx.write_gexf(git_network, "network_outputs/git.gexf")

# parts = community.best_partition(git_graph)
# values = [parts.get(node) for node in git_graph.nodes()]
# print(values)
vis_network.show_buttons(filter_=['physics'])
vis_network.show("network_outputs/git.html")
