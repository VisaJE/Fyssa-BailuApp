from flask import Flask, request
from time import gmtime, strftime
import psycopg2
import configparser

app = Flask(__name__)
cp = configparser.ConfigParser()
cp.read('/opt/secrets/bailu_config')

pgsql_conn = psycopg2.connect('dbname={} user={} host={} password={}'.format(cp['DEFAULT']['PGSQL_DATABASE'], cp['DEFAULT']['PGSQL_USER'], cp['DEFAULT']['PGSQL_HOST'], cp['DEFAULT']['PGSQL_PASSWORD']))
cursor = pgsql_conn.cursor()

@app.route('/bailu/threshold', methods=['get'])
def threshold():
    return ('%s'%cp['DEFAULT']['TEMPERATURE_THRESHOLD'], 200)

@app.route('/bailu/name/<name_id>', methods=['get'])
def getName(name_id):
    query = 'SELECT name FROM bailutable WHERE mac=%s;'
    params = (name_id,)
    print(query, params)  
    cursor.execute(query, params)
    result = cursor.fetchone()
    if result is not None:
      return (name_id + result[0], 200)
    else:
      return (name_id+'Anonymous partyer', 200)

@app.route('/bailu/name/insert', methods=['post'])
def insertName():
    name = request.args.get('name')
    serial = request.args.get('mac')
    query = 'UPDATE bailutable SET name=%s WHERE mac=%s;'
    params = ( name, serial)
    print(query, params)  
    cursor.execute(query, params)
    if cursor.rowcount == 0:
      query = 'INSERT INTO bailutable (name, mac) VALUES (%s, %s)'
      print(query, params)  
      cursor.execute(query, params)
    pgsql_conn.commit()
    return (serial, 200)

if __name__ == '__main__':
     app.run(host='0.0.0.0')
