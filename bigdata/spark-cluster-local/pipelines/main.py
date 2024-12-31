from pyspark.sql import SparkSession


def main():
    # data file taken from https://bustime.mta.info/wiki/Developers/ArchiveData
    file = "/workspace/data/B63.csv"
    sql = SparkSession.builder.appName("bustime-app").getOrCreate()

    df = sql.read.load(file, format="csv", inferSchema="true", sep=",", header="true")
    df.show()


if __name__ == "__main__":
    main()
