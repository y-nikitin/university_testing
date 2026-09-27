import os

def connect():
    import psycopg
    return psycopg.connect(host=os.getenv("DB_HOST", "localhost"), port=os.getenv("DB_PORT", "5432"),
        dbname=os.getenv("DB_NAME", "delivery_variant07_test"), user=os.getenv("DB_USER", "delivery_student"),
        password=os.getenv("DB_PASSWORD", ""), autocommit=True)
