import re

ignore_keywords = ['id', 'creation', 'modification']


def document_from_database_info(database_info):
    return ' '.join([clean_database_field(column) for column in database_info['columns']] +
                    [clean_database_field(database_info['name'])])


def clean_database_field(name):
    numberless = re.sub(r'\d+', '', name)
    stripped = numberless[5:] if len(numberless) > 4 and numberless[4] == '_' else numberless
    for keyword in ignore_keywords:
        stripped = re.sub(keyword, '', stripped)
    return re.sub(r'_', ' ', stripped)
