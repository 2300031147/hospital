# Use the official lightweight Python image.
FROM python:3.11-slim-bookworm

# Prevent Python from writing .pyc files & allow stdout logging without buffering
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

# Create a non-root system user
RUN groupadd -r appuser && useradd -r -g appuser appuser

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# Install python dependencies using requirements
COPY requirements.txt .
RUN pip install --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt

# Copy the complete backend structure into the docker container
COPY . /app

# Set ownership to the non-root user
RUN chown -R appuser:appuser /app

# Switch to the non-root user
USER appuser

# Expose port (Internal Docker network mapping)
EXPOSE 8000

# Start command tailored for production (uvicorn worker)
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
