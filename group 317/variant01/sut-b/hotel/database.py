import os

def connect():
    import psycopg
    return psycopg.connect(host=os.getenv("DB_HOST", "localhost"), port=os.getenv("DB_PORT", "5432"),
        dbname=os.getenv("DB_NAME", "hotel317_variant01_test"), user=os.getenv("DB_USER", "hotel_student"),
        password=os.getenv("DB_PASSWORD", ""), autocommit=True)
