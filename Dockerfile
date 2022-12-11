FROM clojure

WORKDIR /app

COPY . .

ENV PORT=7777

EXPOSE 7777

EXPOSE 8080

EXPOSE 12345

EXPOSE 8888

RUN ["apt-get", "update"]

RUN ["apt-get", "install", "-y", "vim"]

RUN ["apt-get", "install", "-y", "emacs"]

CMD ["make", "dev"]
