FROM python:3.12-slim
WORKDIR /tests
COPY tests/requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY tests/ ./
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
CMD ["pytest", "-q", "--junitxml=/reports/multiservice.xml"]
