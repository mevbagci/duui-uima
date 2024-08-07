FROM python:3.8

WORKDIR /usr/src/app

# This port is specified by the DUUI API
EXPOSE 9714

# Install dependencies
RUN pip install setuptools wheel
COPY ./requirements.txt ./requirements.txt
RUN pip install --no-deps -r requirements.txt

# Preload Models during Docker build
COPY ./src/main/python/models/preload_models.py ./src/main/python/models/preload_models.py
COPY ./src/main/python/models/cardiffnlp_twitter_xlm_roberta_base_sentiment.py ./src/main/python/models/cardiffnlp_twitter_xlm_roberta_base_sentiment.py
RUN ["python3", "./src/main/python/models/cardiffnlp_twitter_xlm_roberta_base_sentiment.py"]

# Log level
ARG TTLAB_DUUI_TRANSFORMERS_SENTIMENT_EXAMPLE_LOG_LEVEL="DEBUG"
ENV TTLAB_DUUI_TRANSFORMERS_SENTIMENT_EXAMPLE_LOG_LEVEL=$TTLAB_DUUI_TRANSFORMERS_SENTIMENT_EXAMPLE_LOG_LEVEL

# Model cache size
ARG TTLAB_DUUI_TRANSFORMERS_SENTIMENT_EXAMPLE_MODEL_CACHE_SIZE=1
ENV TTLAB_DUUI_TRANSFORMERS_SENTIMENT_EXAMPLE_MODEL_CACHE_SIZE=$TTLAB_DUUI_TRANSFORMERS_SENTIMENT_EXAMPLE_MODEL_CACHE_SIZE

# Meta data
ARG TTLAB_DUUI_TRANSFORMERS_SENTIMENT_EXAMPLE_ANNOTATOR_NAME="duui-transformers-sentiment-example"
ENV TTLAB_DUUI_TRANSFORMERS_SENTIMENT_EXAMPLE_ANNOTATOR_NAME=$TTLAB_DUUI_TRANSFORMERS_SENTIMENT_EXAMPLE_ANNOTATOR_NAME
ARG TTLAB_DUUI_TRANSFORMERS_SENTIMENT_EXAMPLE_ANNOTATOR_VERSION="dev"
ENV TTLAB_DUUI_TRANSFORMERS_SENTIMENT_EXAMPLE_ANNOTATOR_VERSION=$TTLAB_DUUI_TRANSFORMERS_SENTIMENT_EXAMPLE_ANNOTATOR_VERSION

# Enable offline mode for Hugging Face
ARG TTLAB_DUUI_TRANSFORMERS_SENTIMENT_EXAMPLE_TRANSFORMERS_OFFLINE=1
ENV TRANSFORMERS_OFFLINE=$TTLAB_DUUI_TRANSFORMERS_SENTIMENT_EXAMPLE_TRANSFORMERS_OFFLINE

# Copy all needed scripts
COPY ./src/main/resources/TypeSystemSentiment.xml ./src/main/resources/TypeSystemSentiment.xml
COPY ./src/main/python/__init__.py ./src/main/python/__init__.py
COPY ./src/main/python/duui/ ./src/main/python/duui/
COPY ./src/main/python/duui_transformers_sentiment.py ./src/main/python/duui_transformers_sentiment.py
COPY ./src/main/lua/duui_transformers_sentiment.lua ./src/main/lua/duui_transformers_sentiment.lua

# Start the Python server
ENTRYPOINT ["uvicorn", "src.main.python.duui_transformers_sentiment:app", "--host", "0.0.0.0", "--port" ,"9714"]
CMD ["--workers", "1"]
