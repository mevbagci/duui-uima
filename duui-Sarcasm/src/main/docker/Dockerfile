FROM python:3.10

WORKDIR /usr/src/app

EXPOSE 9714

COPY ./reqiurements.txt ./reqiurements.txt
RUN pip install -r reqiurements.txt

RUN python -c "from transformers import pipeline; pipeline('text-classification', model='helinivan/multilingual-sarcasm-detector')"


# service script
COPY ./src/main/python/TypeSystemSarcasm.xml ./TypeSystemSarcasm.xml
COPY ./src/main/python/sarcasm_detection.py ./sarcasm_detection.py
COPY ./src/main/python/duui_Sarcasm.lua ./duui_Sarcasm.lua
COPY ./src/main/python/duui_sarcasm.py ./duui_sarcasm.py


# meta data
ARG ANNOTATOR_NAME="duui-Sarcasm:app"
ENV ANNOTATOR_NAME=$ANNOTATOR_NAME
ARG ANNOTATOR_VERSION="unset"
ENV ANNOTATOR_VERSION=$ANNOTATOR_VERSION

# log level
ARG LOG_LEVEL="DEBUG"
ENV LOG_LEVEL=$LOG_LEVEL

# config
ARG MODEL_CACHE_SIZE=1
ENV MODEL_CACHE_SIZE=$MODEL_CACHE_SIZE
ARG MODEL_NAME=""
ENV MODEL_NAME=$MODEL_NAME
ARG MODEL_VERSION=""
ENV MODEL_VERSION=$MODEL_VERSION
ARG MODEL_SOURCE=""
ENV MODEL_SOURCE=$MODEL_SOURCE
ARG MODEL_LANG=""
ENV MODEL_LANG=$MODEL_LANG



ENTRYPOINT ["uvicorn", "duui_sarcasm:app", "--host", "0.0.0.0", "--port" ,"9714"]
CMD ["--workers", "1"]

