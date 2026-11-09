package in.aitechapp.teluguoffline;

/**
 * Conservative, fully local PCM16 post-processing for generated WAV exports.
 * It reduces sub-bass rumble and some low-mid boxiness, while gently lifting
 * vocal presence. Not a denoiser, source separator or neural voice converter.
 * Each WAV utterance must start with a fresh instance to reset biquad state.
 */
public final class AudioPolish {
    private final int channels;
    private final Biquad[][] bands;
    private final int fadeFrames;

    public AudioPolish(int sampleRate, int channels) {
        if (sampleRate < 8000 || sampleRate > 192000 || channels < 1 || channels > 2) {
            throw new IllegalArgumentException("Unsupported PCM sample rate or channels");
        }
        this.channels = channels;
        this.fadeFrames = Math.max(1, Math.round(sampleRate * 0.006f));
        this.bands = new Biquad[channels][3];
        for (int c=0; c<channels; c++) {
            bands[c][0] = Biquad.highpass(sampleRate, 85.0, 0.707);
            bands[c][1] = Biquad.peak(sampleRate, 440.0, 0.80, -3.0);
            bands[c][2] = Biquad.peak(sampleRate, 2400.0, 0.70, +1.7);
        }
    }

    /**
     * Process a buffer of signed PCM16 LE audio in-place without buffering a full
     * 10–20 minute render. startByteOffset counts from the start of THIS WAV's
     * data section. totalDataBytes is the size of that same PCM data section.
     */
    public void processInPlace(byte[] buf, int count, long startByteOffset, long totalDataBytes) {
        int bytesPerFrame = channels * 2;
        if (count < 0 || count > buf.length || count % bytesPerFrame != 0 ||
            startByteOffset < 0 || startByteOffset % bytesPerFrame != 0 ||
            totalDataBytes < count || totalDataBytes % bytesPerFrame != 0 ||
            startByteOffset + count > totalDataBytes) {
            throw new IllegalArgumentException("PCM16 requires whole frames and valid byte offsets");
        }
        long totalFrames = totalDataBytes / bytesPerFrame;
        long firstFrame = startByteOffset / bytesPerFrame;
        for (int frame=0; frame<count/bytesPerFrame; frame++) {
            long currentFrame = firstFrame + frame;
            double fade = 1.0;
            if (currentFrame < fadeFrames) fade = Math.min(fade, (currentFrame + 1.0) / fadeFrames);
            long framesRemaining = totalFrames - currentFrame;
            if (framesRemaining < fadeFrames) fade = Math.min(fade, framesRemaining / (double)fadeFrames);
            for (int c=0; c<channels; c++) {
                int i = frame * bytesPerFrame + c * 2;
                short sample = (short)((buf[i] & 0xff) | ((buf[i+1] & 0xff) << 8));
                double value = sample / 32768.0;
                for (Biquad filter : bands[c]) value = filter.process(value);
                // Leave peak headroom. Slight limiter is only a safety net.
                value *= 0.86 * fade;
                value = Math.max(-0.89, Math.min(0.89, value));
                int out = Math.max(-32768, Math.min(32767, (int)Math.round(value * 32767.0)));
                buf[i] = (byte)(out & 0xff);
                buf[i+1] = (byte)((out >> 8) & 0xff);
            }
        }
    }

    private static final class Biquad {
        final double b0,b1,b2,a1,a2;
        double x1=0,x2=0,y1=0,y2=0;
        Biquad(double b0,double b1,double b2,double a0,double a1,double a2) {
            this.b0=b0/a0; this.b1=b1/a0; this.b2=b2/a0;
            this.a1=a1/a0; this.a2=a2/a0;
        }
        double process(double x) {
            double y=b0*x+b1*x1+b2*x2-a1*y1-a2*y2;
            x2=x1;x1=x;y2=y1;y1=y;
            return y;
        }
        static Biquad highpass(int sampleRate,double hz,double q) {
            double w=2*Math.PI*hz/sampleRate,c=Math.cos(w),a=Math.sin(w)/(2*q);
            return new Biquad((1+c)/2,-(1+c),(1+c)/2,1+a,-2*c,1-a);
        }
        static Biquad peak(int sampleRate,double hz,double q,double dB) {
            double w=2*Math.PI*hz/sampleRate,c=Math.cos(w),alpha=Math.sin(w)/(2*q),A=Math.pow(10,dB/40);
            return new Biquad(1+alpha*A,-2*c,1-alpha*A,1+alpha/A,-2*c,1-alpha/A);
        }
    }
}
