package kin.sdk.core;


import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.util.Base64;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableEntryException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONException;
import org.json.JSONObject;

@RequiresApi(api = Build.VERSION_CODES.M)
class EncryptorImplV23 implements Encryptor {

    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final String AES_MODE = "AES/GCM/NoPadding";
    private static final String ALIAS = "KinKeyStore";
    private static final String JSON_IV = "iv";
    private static final String JSON_CIPHER = "cipher";
    private static final int KEY_SIZE = 128;

    EncryptorImplV23() {
    }

    @Override
    public String encrypt(String secret) throws CryptoException {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
            keyStore.load(null);
            return aesEncrypt(ALIAS, secret);
        } catch (Exception e) {
            throw new CryptoException(e);
        }
    }

    @Override
    public String decrypt(String encryptedSecret) throws CryptoException {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
            keyStore.load(null);
            return aesDecrypt(encryptedSecret, keyStore);
        } catch (Exception e) {
            throw new CryptoException(e);
        }
    }

    private String aesDecrypt(String encryptedSecret, KeyStore keyStore)
        throws UnrecoverableEntryException, NoSuchAlgorithmException, KeyStoreException,
        NoSuchProviderException, NoSuchPaddingException, InvalidKeyException, IOException,
        BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException, JSONException {
        JSONObject jsonObject = new JSONObject(encryptedSecret);
        String ivBase64String = jsonObject.getString(JSON_IV);
        String cipherBase64String = jsonObject.getString(JSON_CIPHER);
        byte[] ivBytes = Base64.decode(ivBase64String, Base64.DEFAULT);
        byte[] encryptedSecretBytes = Base64.decode(cipherBase64String, Base64.DEFAULT);

        return performDecryption(keyStore, ivBytes, encryptedSecretBytes);
    }

    @NonNull
    private String performDecryption(KeyStore keyStore, byte[] ivBytes, byte[] encryptedSecretBytes)
        throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, UnrecoverableEntryException, KeyStoreException, IllegalBlockSizeException, BadPaddingException, UnsupportedEncodingException {
        final Cipher cipher = Cipher.getInstance(AES_MODE);
        final GCMParameterSpec spec = new GCMParameterSpec(KEY_SIZE, ivBytes);
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(ALIAS, keyStore), spec);

        byte[] decryptedBytes = cipher.doFinal(encryptedSecretBytes);
        return new String(decryptedBytes, 0, decryptedBytes.length, "UTF-8");
    }

    private SecretKey getSecretKey(final String alias, KeyStore keyStore) throws NoSuchAlgorithmException,
        UnrecoverableEntryException, KeyStoreException {
        return ((KeyStore.SecretKeyEntry) keyStore.getEntry(alias, null)).getSecretKey();
    }

    private String aesEncrypt(String alias, String secret)
        throws NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, UnsupportedEncodingException, KeyStoreException, JSONException {
        SecretKey secretKey = generateAESSecretKey(alias);
        return performEncryption(secretKey, secret.getBytes("UTF-8"));
    }

    private SecretKey generateAESSecretKey(String alias)
        throws NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);
        keyGenerator.init(
            new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return keyGenerator.generateKey();
    }

    private String performEncryption(SecretKey secretKey, byte[] secretBytes)
        throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, JSONException, InvalidAlgorithmParameterException {

        Cipher cipher = Cipher.getInstance(AES_MODE);

        cipher.init(Cipher.ENCRYPT_MODE, secretKey);

        byte[] encryptedBytes = cipher.doFinal(secretBytes);
        String base64Iv = Base64.encodeToString(cipher.getIV(), Base64.DEFAULT);
        String base64Encrypted = Base64.encodeToString(encryptedBytes, Base64.DEFAULT);
        return toJson(base64Iv, base64Encrypted);
    }

    private String toJson(String base64Iv, String base64EncyrptedSecret) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(JSON_IV, base64Iv);
        jsonObject.put(JSON_CIPHER, base64EncyrptedSecret);
        return jsonObject.toString();
    }

}
