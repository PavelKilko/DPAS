# Before running the script, make sure that:
# 1) The corresponding virtual environment with all necessary packages is activated.
# 2) The corresponding broker is running
# 3) The corresponding server is running
celery -A main.celery worker --loglevel=info