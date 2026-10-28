import os

def connect():
    import psycopg
    return psycopg.connect(host=os.getenv("SECURITY_DB_HOST", "localhost"), port=os.getenv("SECURITY_DB_PORT", "55433"),
        dbname=os.getenv("SECURITY_DB_NAME", "hotel317_variant02_security"), user=os.getenv("SECURITY_DB_USER", "hotel_security"),
        password=os.getenv("SECURITY_DB_PASSWORD", ""), autocommit=True)
