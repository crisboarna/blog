#!/bin/bash

# Exit if any command fails
set -e

# Check if bucket name is provided as a command line argument
if [ "$#" -ne 3 ]; then
    echo "Error: Bucket name or env not provided. Usage: ./certificates.sh <bucket_name> <env> <region>"
    exit 1
fi

# Set bucket name
bucket_name=$1
env=$2
region=$3

# Check if the ca.crt file exists in the S3 bucket
if aws s3 ls "s3://${bucket_name}/${env}/certificates/ca.crt" &> /dev/null
then
    echo "ca.crt already exists in the S3 bucket. Exiting..."
    exit 0
fi

echo $PWD

# Downloading dependency
git clone https://github.com/OpenVPN/easy-rsa.git

# Entering easy-rsa3 directory
cd easy-rsa/easyrsa3

# Init PKI
./easyrsa init-pki

echo "set_var EASYRSA_REQ_COUNTRY    \"UK\"
      set_var EASYRSA_REQ_PROVINCE   \"London\"
      set_var EASYRSA_REQ_CITY       \"London\"
      set_var EASYRSA_REQ_ORG        \"EngineerMindscape\"
      set_var EASYRSA_REQ_EMAIL      \"contact@crisboarna.com\"
      set_var EASYRSA_REQ_OU         \"EM\"" > pki/vars

# Build CA
echo "yes" | ./easyrsa build-ca nopass <<< "${env}.vpn.blog.engineermindscape.com"

# Generate a key pair for the server
echo "yes" | ./easyrsa build-server-full ${env}.vpn.blog.engineermindscape.com nopass

# Generate a key pair for the client
echo "yes" | ./easyrsa build-client-full client.${env}.vpn.blog.engineermindscape.com nopass

mkdir ../../certificates/
cp pki/ca.crt ../../certificates/
cp pki/private/${env}.vpn.blog.engineermindscape.com.key ../../certificates/
cp pki/issued/${env}.vpn.blog.engineermindscape.com.crt ../../certificates/
cp pki/private/client.${env}.vpn.blog.engineermindscape.com.key ../../certificates/
cp pki/issued/client.${env}.vpn.blog.engineermindscape.com.crt ../../certificates
cd ../../certificates/

echo "Certificates generated successfully."

echo "Uploading the certificates to the S3 bucket"

aws s3 cp ca.crt "s3://${bucket_name}/${env}/certificates/ca.crt"
aws s3 cp ${env}.vpn.blog.engineermindscape.com.key "s3://${bucket_name}/${env}/certificates/${env}.vpn.blog.engineermindscape.com.key"
aws s3 cp ${env}.vpn.blog.engineermindscape.com.crt "s3://${bucket_name}/${env}/certificates/${env}.vpn.blog.engineermindscape.com.crt"
aws s3 cp client.${env}.vpn.blog.engineermindscape.com.key "s3://${bucket_name}/${env}/certificates/client.${env}.vpn.blog.engineermindscape.com.key"
aws s3 cp client.${env}.vpn.blog.engineermindscape.com.crt "s3://${bucket_name}/${env}/certificates/client.${env}.vpn.blog.engineermindscape.com.crt"

echo "Certificates uploaded to S3 successfully."

echo "Uploading to ACM"
server_cert_arn=$(aws acm import-certificate --certificate fileb://${env}.vpn.blog.engineermindscape.com.crt --private-key fileb://${env}.vpn.blog.engineermindscape.com.key --certificate-chain fileb://ca.crt --region ${region} --no-cli-pager | jq -r '.CertificateArn')
client_cert_arn=$(aws acm import-certificate --certificate fileb://client.${env}.vpn.blog.engineermindscape.com.crt --private-key fileb://client.${env}.vpn.blog.engineermindscape.com.key --certificate-chain fileb://ca.crt --region ${region} --no-cli-pager | jq -r '.CertificateArn')

echo "SERVER_CERT: $server_cert_arn"
echo "CLIENT_CERT: $client_cert_arn"