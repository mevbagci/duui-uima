FROM python:3.8

WORKDIR /usr/src/app

EXPOSE 9714

RUN pip install setuptools wheel

COPY ./requirements.txt ./requirements.txt
RUN pip install --no-deps -r requirements.txt

ARG DUUI_SENTENCIZER_SEGTOK_LOG_LEVEL="DEBUG"
ENV DUUI_SENTENCIZER_SEGTOK_LOG_LEVEL=$DUUI_SENTENCIZER_SEGTOK_LOG_LEVEL

ARG DUUI_SENTENCIZER_SEGTOK_ANNOTATOR_NAME="duui-sentencizer-segtok"
ENV DUUI_SENTENCIZER_SEGTOK_ANNOTATOR_NAME=$DUUI_SENTENCIZER_SEGTOK_ANNOTATOR_NAME
ARG DUUI_SENTENCIZER_SEGTOK_ANNOTATOR_VERSION="unset"
ENV DUUI_SENTENCIZER_SEGTOK_ANNOTATOR_VERSION=$DUUI_SENTENCIZER_SEGTOK_ANNOTATOR_VERSION

COPY ./src/main/resources/TypeSystem.xml ./TypeSystem.xml
COPY ./src/main/python/duui.py ./duui.py
COPY ./src/main/lua/communication.lua ./communication.lua

ENTRYPOINT ["uvicorn", "duui:app", "--host", "0.0.0.0", "--port" ,"9714"]
CMD ["--workers", "1"]
