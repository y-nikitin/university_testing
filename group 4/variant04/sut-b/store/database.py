import os


def connect():
    import psycopg

    return psycopg.connect(
        host=os.getenv("DB_HOST", "localhost"),
        port=os.getenv("DB_PORT", "5432"),
        dbname=os.getenv("DB_NAME", "store_variant04_test"),
        user=os.getenv("DB_USER", "store_student"),
        password=os.getenv("DB_PASSWORD", ""),
        autocommit=True,
    )
