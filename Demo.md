# Prerequisites
- [Docker and Docker Compose](https://www.docker.com/)
- [MacFuse](https://github.com/macfuse/macfuse/wiki/Getting-Started)

# Running the F1r3fly shard locally

Run shard locally:
```sh
docker-compose -f ./docker-compose.yml up -d
```

Wait for "Listening for traffic" logs:
```sh
docker-compose -f ./docker-compose.yml logs
```
Logs should be like: 
```text
Listening for traffic on rnode://cfae6a0c885d734908f8c756fb0519d2df7fbcec@178.150.31.10?protocol=40400&discovery=40404
```

Local shard will be configured with the following configurations:
- [REV Addresses (wallets.txt)](./data/genesis/wallets.txt)
- nodes (see [docker-compose.yml](docker-compose.yml) for Node configs): 
  - bootstrap node: `localhost:40402`
  - observer node: `localhost:40412`

# Run F1r3Drive app

1. Download the latest `f1r3drive-*.jar` from the [GitHub Releases page](https://github.com/f1r3fly-io/F1R3FLYFS/releases).

2. Run F1r3Drive app with REV Address and Private key. That must be assosiated with some REV address from [wallets.txt](./data/genesis//wallets.txt):

**Manual Propose Option:**
- `--manual-propose true`: Manual deployment flow with propose and finalization waiting (for development/testing only)
- `--manual-propose false`: Deploy only, skip propose and finalization waiting (production shards will do auto-propose)

- If you build the jar locally, run:
```sh
java -jar ./build/libs/f1r3drive-0.1.0.jar ~/demo-f1r3drive \
   --cipher-key-path ~/cipher.key \
   --validator-host localhost --validator-port 40402 \
   --observer-host localhost --observer-port 40412 \
   --manual-propose true \
   --rev-address 11112ZM9yrfaTrzCCbKjPbxBncjNCkMFsPqtcLFvhBf4Kqx6rpir2w --private-key a8cf01d889cc6ef3119ecbd57301036a52c41ae6e44964e098cb2aefa4598954
```
- If you downloaded the JAR to your `~/Downloads` folder, run:
```sh
java -jar ~/Downloads/f1r3drive-0.1.0.jar ~/demo-f1r3drive \
   --cipher-key-path ~/cipher.key \
   --validator-host localhost --validator-port 40402 \
   --observer-host localhost --observer-port 40412 \
   --manual-propose true \
   --rev-address 11112ZM9yrfaTrzCCbKjPbxBncjNCkMFsPqtcLFvhBf4Kqx6rpir2w --private-key a8cf01d889cc6ef3119ecbd57301036a52c41ae6e44964e098cb2aefa4598954
```

# Demo

Creating a tiny file inside ~/demo-f1r3drive folder:

```sh
echo "abc" > ~/demo-f1r3drive/11112ZM9yrfaTrzCCbKjPbxBncjNCkMFsPqtcLFvhBf4Kqx6rpir2w/demo.txt
ls -lh ~/demo-f1r3drive/11112ZM9yrfaTrzCCbKjPbxBncjNCkMFsPqtcLFvhBf4Kqx6rpir2w/demo.txt
cat ~/demo-f1r3drive/11112ZM9yrfaTrzCCbKjPbxBncjNCkMFsPqtcLFvhBf4Kqx6rpir2w/demo.txt
```

Copy 1M file inside ~/demo-f1r3drive folder:

```sh
# generating binary file with 1M size
dd if=/dev/zero of=large_data.txt bs=1m count=1

# copy file (use rsync for progress logs)
cp large_data.txt ~/demo-f1r3drive/11112ZM9yrfaTrzCCbKjPbxBncjNCkMFsPqtcLFvhBf4Kqx6rpir2w/
# OR: rsync -av --progress large_data.txt ~/demo-f1r3drive/11112ZM9yrfaTrzCCbKjPbxBncjNCkMFsPqtcLFvhBf4Kqx6rpir2w/

# wait for some time, then verify the file is copied
ls -lh ~/demo-f1r3drive/11112ZM9yrfaTrzCCbKjPbxBncjNCkMFsPqtcLFvhBf4Kqx6rpir2w/large_data.txt
```

# Cleanup

Stop all processes and remove all files:

```sh
# hit Ctrl+C in F1r3Drive app

# or kill if stuck
ps aux | grep java | grep -v grep | awk '{print $2}' | xargs kill -9


# force unmount ~/demo-f1r3drive if stuck
sudo diskutil umount force ~/demo-f1r3drive

# stop Shard
docker-compose -f docker-compose.yml down

# ~/demo-f1r3drive has to be empty folder
# delete ~/demo-f1r3drive before running the next time
rm -rf ~/demo-f1r3drive

# remove large_data.txt
rm -f large_data.txt
```
