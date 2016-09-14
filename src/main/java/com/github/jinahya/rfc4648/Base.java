/*
 * Copyright 2016 Jin Kwon &lt;jinahya_at_gmail.com&gt;.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.jinahya.rfc4648;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.CharArrayReader;
import java.io.CharArrayWriter;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;


/**
 *
 * @author Jin Kwon &lt;jinahya_at_gmail.com&gt;
 */
public abstract class Base {


    /**
     * Default pad character.
     */
    protected static final char PAD = '=';


    /**
     * MAGIC NUMBER: OCTET SIZE.
     */
    private static final int OCTET_SIZE = 8;


    /**
     * MAGIC NUMBER: ASCII SIZE.
     */
    private static final int ASCII_SIZE = 128;


    /**
     * MAGIC NUMBER: SMALLEST VISIBLE ASCII.
     */
    private static final int SMALLEST_VISIBLE_ASCII = 33;


    /**
     * Returns the Least Common Multiple value for given two operands.
     *
     * @param a the first operand
     * @param b the second operand
     *
     * @return calculated least common multiple
     */
    private static int lcm(final int a, final int b) {
        return ((a * b) / gcd(a, b));
    }


    /**
     * Returns the Greatest Common Divisor for given two operands.
     *
     * @param a the first operand
     * @param b the second operand
     *
     * @return calculated great common devisor
     */
    private static int gcd(final int a, final int b) {
        if (b == 0) {
            return a;
        } else {
            return gcd(b, a % b);
        }
    }


    static int bpc(final int alphabets) {
        return (int) (Math.log(alphabets) / Math.log(2.0d));
    }


    static int bpw(final int alphabets) {
        return lcm(OCTET_SIZE, bpc(alphabets)) / OCTET_SIZE;
    }


    static int cpw(final int alphabets) {
        return (bpw(alphabets) * OCTET_SIZE) / bpc(alphabets);
    }


    static byte[] numbers(final byte[] characters) {
        final byte[] numbers = new byte[256];
        for (int i = 0; i < numbers.length; i++) {
            numbers[i] = -1;
        }
        for (byte i = 0; i < characters.length; i++) {
            numbers[characters[i]] = i;
        }
        return numbers;
    }


    /**
     * Create a new instance.
     *
     * @param alphabet alphabet to be used
     * @param padding flag for padding
     *
     * @deprecated
     */
    @Deprecated
    Base(final byte[] alphabet, final boolean padding) {
        super();

        if (alphabet == null) {
            throw new IllegalArgumentException("null alphabet");
        }

        if (alphabet.length == 0) {
            throw new IllegalArgumentException("empty alphabet");
        }

        characters = alphabet;

        numbers = new byte[ASCII_SIZE - SMALLEST_VISIBLE_ASCII + 1];
        for (int i = 0; i < numbers.length; i++) {
            numbers[i] = -1;
        }
        for (byte i = 0; i < characters.length; i++) {
            numbers[characters[i] - SMALLEST_VISIBLE_ASCII] = i;
        }

        this.pads = padding;

        bitsPerChar = (int) (Math.log(characters.length) / Math.log(2.0d));
        bytesPerWord = lcm(OCTET_SIZE, bitsPerChar) / OCTET_SIZE;
        charsPerWord = bytesPerWord * OCTET_SIZE / bitsPerChar;
    }


    Base(final byte[] characters, final byte[] numbers, final boolean pads,
         final int bitsPerChar, final int bytesPerWord,
         final int charsPerWord) {

        super();

        this.characters = characters;
        this.numbers = numbers;
        this.pads = pads;
        this.bitsPerChar = bitsPerChar;
        this.bytesPerWord = bytesPerWord;
        this.charsPerWord = charsPerWord;
    }


    /**
     *
     * @param input
     *
     * @return
     *
     * @throws IOException
     */
    public char[] encode(final byte[] input) throws IOException {

        if (input == null) {
            throw new IllegalArgumentException("null input");
        }

        return encode(new ByteArrayInputStream(input));
    }


    /**
     *
     * @param input
     *
     * @return
     *
     * @throws IOException
     */
    public char[] encode(final InputStream input) throws IOException {

        if (input == null) {
            throw new IllegalArgumentException("null input");
        }

        final CharArrayWriter output = new CharArrayWriter();

        encode(input, output);
        output.flush();

        return output.toCharArray();
    }


    /**
     *
     * @param input binary input
     * @param output character output
     *
     * @throws IOException if an I/O error occurs
     */
    public final void encode(final InputStream input, final Writer output)
        throws IOException {

        if (input == null) {
            throw new IllegalArgumentException("null input");
        }

        if (output == null) {
            throw new IllegalArgumentException("null output");
        }

        encode(new BitInput(input), output);
    }


    /**
     *
     * @param input binary input
     * @param output character output
     *
     * @throws IOException if an I/O error occurs
     */
    private void encode(final BitInput input, final Writer output)
        throws IOException {

        if (input == null) {
            throw new IllegalArgumentException("null input");
        }

        if (output == null) {
            throw new IllegalArgumentException("null output");
        }

        outer:
        while (true) {

            for (int i = 0; i < charsPerWord; i++) {

                int available = OCTET_SIZE - ((bitsPerChar * i) % OCTET_SIZE);

                if (available >= bitsPerChar) {
                    try {
                        int unsigned = input.readUnsignedInt(bitsPerChar);
                        output.write(characters[unsigned]);
                    } catch (EOFException eofe) { // i == 0
                        break outer;
                    }
                } else { // need next octet
                    int required = bitsPerChar - available;
                    int unsigned
                        = (input.readUnsignedInt(available) << required);
                    try {
                        unsigned |= input.readUnsignedInt(required);
                        output.write(characters[unsigned]);
                    } catch (EOFException eofe) {
                        output.write(characters[unsigned]);
                        if (pads) {
                            for (int j = i + 1; j < charsPerWord; j++) {
                                output.write(PAD);
                            }
                        }
                        break outer;
                    }
                }
            }
        }
    }


    /**
     *
     * @param input
     *
     * @return
     *
     * @throws IOException
     */
    public final byte[] decode(final char[] input) throws IOException {

        if (input == null) {
            throw new IllegalArgumentException("null input");
        }

        return decode(new CharArrayReader(input));
    }


    /**
     *
     * @param input
     *
     * @return
     *
     * @throws IOException
     */
    public final byte[] decode(final Reader input) throws IOException {

        if (input == null) {
            throw new IllegalArgumentException("null input");
        }

        final ByteArrayOutputStream output = new ByteArrayOutputStream();

        decode(input, output);
        output.flush();

        return output.toByteArray();
    }


    /**
     *
     * @param input character input
     * @param output binary output
     *
     * @throws IOException if I/O error occurs
     */
    public final void decode(final Reader input, final OutputStream output)
        throws IOException {

        if (input == null) {
            throw new IllegalArgumentException("null input");
        }

        if (output == null) {
            throw new IllegalArgumentException("null outpute");
        }

        decode(input, new BitOutput(output));
    }


    /**
     *
     * @param input character input
     * @param output binary output
     *
     * @throws IOException if I/O error occurs
     */
    private void decode(final Reader input, final BitOutput output)
        throws IOException {

        outer:
        while (true) {

            int c;

            for (int i = 0; i < charsPerWord; i++) {

                c = input.read();

                if (c == -1) { // end of stream

                    if (i == 0) { // first character in a word; ok
                        break outer;
                    }

                    if (((i * bitsPerChar) % OCTET_SIZE) >= bitsPerChar) {
                        throw new EOFException("not finished properly");
                    }

                    if (!pads) {
                        break outer;
                    }

                    throw new EOFException("not finished properly");

                } else if (c == PAD) {

                    if (!pads) {
                        throw new IOException("bad padding; no pads allowed");
                    }

                    if (i == 0) { // first character in a word
                        throw new IOException("bad padding");
                    }

                    if (((i * bitsPerChar) % OCTET_SIZE) >= bitsPerChar) {
                        throw new IOException("bad padding");
                    }

                    for (int j = i + 1; j < charsPerWord; j++) {
                        c = input.read(); // pad
                        if (c == -1) { // end of stream?
                            throw new EOFException("not finished properly");
                        }
                        if (c != PAD) { // not the pad char?
                            throw new IOException("bad padding");
                        }
                    }

                    break outer;

                } else {

                    int value = numbers[c - SMALLEST_VISIBLE_ASCII];
                    if (value == -1) {
                        throw new IOException("bad character: " + (char) c);
                    }
                    output.writeUnsignedInt(bitsPerChar, value);
                }
            }
        }
    }


    /**
     * Characters for encoding.
     */
    private final byte[] characters;


    /**
     * Characters for decoding.
     */
    private final byte[] numbers;


    /**
     * flag for padding.
     */
    private final boolean pads;


    /**
     * number of bits per character.
     */
    private final int bitsPerChar;


    /**
     * number of bytes per word.
     */
    private final int bytesPerWord;


    /**
     * number of characters per word.
     */
    private final int charsPerWord;

}

