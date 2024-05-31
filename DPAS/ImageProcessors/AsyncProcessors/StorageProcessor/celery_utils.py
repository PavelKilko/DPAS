from celery import Celery

CELERY_BROKER_URL = 'redis://localhost:8080'
CELERY_RESULT_BACKEND = 'redis://localhost:8080'
CELERY_QUEUE_NAME = "storage"


def get_celery_app_instance(app):
    celery = Celery(
        app.import_name,
        backend=CELERY_RESULT_BACKEND,
        broker=CELERY_BROKER_URL,
    )
    celery.conf.update(app.config)
    celery.conf.task_default_queue = CELERY_QUEUE_NAME

    class ContextTask(celery.Task):
        def __call__(self, *args, **kwargs):
            with app.app_context():
                return self.run(*args, **kwargs)

    celery.Task = ContextTask

    return celery

