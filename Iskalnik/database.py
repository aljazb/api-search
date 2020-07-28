import sqlalchemy as db

engine = db.create_engine('mysql://root:varnogeslo@127.0.0.1:3306/api_search')
connection = engine.connect()
metadata = db.MetaData()
networks = db.Table('Networks', metadata, autoload=True, autoload_with=engine)
tables = db.Table('Tables', metadata, autoload=True, autoload_with=engine)
columns = db.Table('Columns', metadata, autoload=True, autoload_with=engine)
services = db.Table('ServiceInfos', metadata, autoload=True, autoload_with=engine)
endpoints = db.Table('Endpoints', metadata, autoload=True, autoload_with=engine)


def network_connections(network_type):
    query = db.select([networks]).where(networks.columns.type == network_type).order_by(db.desc(networks.columns.id))
    result_proxy = connection.execute(query)
    timestamp = result_proxy.fetchone()[6]

    query = db.select([networks]).where(networks.columns.timestamp == timestamp)
    result_proxy = connection.execute(query)
    return result_proxy.fetchall()


def database_info():
    query = db.select([tables]).order_by(db.desc(tables.columns.id))
    result_proxy = connection.execute(query)
    timestamp = result_proxy.fetchone()[3]

    query = db.select([tables]).where(tables.columns.timestamp == timestamp)
    result_proxy = connection.execute(query)
    all_tables = result_proxy.fetchall()
    query = db.select([columns]).where(columns.columns.timestamp == timestamp)
    result_proxy = connection.execute(query)
    all_columns = result_proxy.fetchall()
    return sorted([{'name': table[1],
             'columns': [col[1] for col in all_columns if col[2] == table[0]],
             'foreign_keys': table[2].split(',') if table[2] != "" else []} for table in all_tables], key=lambda x: x['name'])


def service_info(only_api_doc=False):
    query = db.select([services]).order_by(db.desc(services.columns.id))
    result_proxy = connection.execute(query)
    timestamp = result_proxy.fetchone()[7]

    query = db.select([services]).where(services.columns.timestamp == timestamp)
    result_proxy = connection.execute(query)
    all_services = result_proxy.fetchall()
    return sorted([{'name': service[1],
             'owner': service[2],
             'has_api_doc': service[3],
             'consul_page_rank': service[4],
             'metrics_page_rank': service[5],
             'metrics_weighted_page_rank': service[6]} for service in all_services
            if not only_api_doc or service[3]], key=lambda x: x['name'])


def endpoint_info():
    query = db.select([endpoints])
    result_proxy = connection.execute(query)
    all_endpoints = result_proxy.fetchall()
    return sorted([{'name': endpoint[1],
             'service': endpoint[2],
             'requests': endpoint[3]} for endpoint in all_endpoints], key=lambda x: x['name'])
