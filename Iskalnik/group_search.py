from database import endpoint_info
from wordnet_network import db_info_network, partition_endpoints
from wordnet_similarity import get_enriched_endpoints


def sort_endpoints(e):
    return 0.0 * e['consul_page_rank'] + 0.0 * e['metrics_page_rank'] + 0.0 * e['metrics_weighted_page_rank'] + 0.0 * e['endpoint_requests']


endpoint_requests = endpoint_info()
enriched_endpoints = get_enriched_endpoints()
partitions_services, partition_service_words = db_info_network()
cached_filters = []
for i in range(12):
    fe = [e for e in enriched_endpoints if e['endpoint']['service'] in partitions_services[i]]
    pe, pw = partition_endpoints(fe)
    cached_filters.append((pe, pw))

for search_iteration in range(100):
    print()
    for p, w in sorted(partition_service_words.items()):
        print("Partition: {}: {} ... ({}) {}".format(p, w, len(partitions_services[p]), partitions_services[p]))
    print("Choose partition:")
    p_id = int(input().strip())

    for i in range(100):
        if i == 0:
            partitions_endpoints, partition_words = cached_filters[p_id]
        else:
            partitions_endpoints, partition_words = partition_endpoints(filtered_endpoints)
        print()
        for p, w in sorted(partition_words.items()):
            print("Partition: {}: {} ... {}".format(p, w, [e['endpoint']['id'] for e in partitions_endpoints[p]]))
        print("Choose partition:")
        p_id = int(input().strip())
        if p_id > 50:
            break
        filtered_endpoints = partitions_endpoints[p_id]
        sorted_filtered_endpoints = sorted(filtered_endpoints, key=sort_endpoints)
        for sfe in sorted_filtered_endpoints[:5]:
            print("{} ({})".format(sfe['endpoint']['path'], sfe['endpoint']['id']))
    # print("Final result: " + filtered_endpoints)



