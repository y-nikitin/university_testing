import os

def connect():
    import psycopg
    return psycopg.connect(host=os.getenv("SECURITY_DB_HOST", "localhost"), port=os.getenv("SECURITY_DB_PORT", "55433"),
        dbname=os.getenv("SECURITY_DB_NAME", "courses_variant12_security"), user=os.getenv("SECURITY_DB_USER", "courses_student"),
        password=os.getenv("SECURITY_DB_PASSWORD", ""), autocommit=True)
