from flask import Flask, request
from config import *
from time import gmtime, strftime
import psycopg2

app = Flask(__name__)

pgsql_conn = psycopg2.connect('dbname={} user={} host={} password={}'.format(PGSQL_DATABASE, PGSQL_USER, PGSQL_HOST, PGSQL_PASSWORD))
cursor = pgsql_conn.cursor()

@app.route('/bailu/threshold', methods=['get'])
def threshold():
    return ('%d'%TEMPERATURE_THRESHOLD, 200)

@app.route('/bailu/name/<int:name_id>', methods=['get'])
def getName(name_id):
    query = 'SELECT name FROM %s WHERE serial=%s;'
    params = (PGSQL_TABLE, int(name_id))
    print(query, params)  
    cursor.execute(query, params)
    result = cursor.fetchone()
    if result is not None:
      return (result[0], 200)
    else return ('Anonymous', 202)

@app.route('/bailu/name/insert', methods=['post'])
def insertName():
    name = request.args.get('name')
    serial = request.args.get('serial')
    query = 'UPDATE %s SET name=%s WHERE serial=%s;'
    params = ( PSQL_TABLE, name, serial)
    print(query, params)  
    cursor.execute(query, params)
    if cursor.rowcount == 0:
      query = 'INSERT INTO %s (name, serial) VALUES (%s, %s)'
      print(query, params)  
      cursor.execute(query, params)
    pgsql_conn.commit()

if __name__ == '__main__':
     app.run(host='0.0.0.0')
