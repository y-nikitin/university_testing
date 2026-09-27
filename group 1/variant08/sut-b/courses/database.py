import os

def connect():
    import psycopg
    return psycopg.connect(host=os.getenv("DB_HOST", "localhost"), port=os.getenv("DB_PORT", "5432"),
        dbname=os.getenv("DB_NAME", "courses_variant08_test"), user=os.getenv("DB_USER", "courses_student"),
        password=os.getenv("DB_PASSWORD", ""), autocommit=True)
