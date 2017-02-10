Android Version of the BikeBump app

In order to change the constants used for sound filtering/ring detection:


SAMPLE_RATE: sample rate used for audio input
BUFFER_SIZE: size of each buffer for FFT
SECONDS_AUDIO: number of seconds of audio being recorded

LOW_PASS_BOUND: value above which sounds are filtered by the low pass filter, in hz
HIGH_PASS_BOUND: value below which sounds are filtered by the high pass filter, in hz
BAND_PASS_WIDTH: width of the range of sound that the band pass will allow through, in hz
BAND_PASS_FREQ: center frequency for the bandpass range, in hz


algorithm calculates by comparing SLOPE1 with the slope between the peak and the point LEFT_GAP bins to the left, and comparing SLOPE2 with the slope between the peak and the point RIGHT_GAP bins to the right. Also, the peak hz must be within TARGET_WIDTH bins of PEAK and at least MIN_SIZE high.

LEFT_GAP: number of bins to the left of the peak the point is taken to calculate the slope to the left
RIGHT_GAP: number of bins to the right of the peak the point is taken to calculate the slope to the right
PEAK: frequency the bell rings at, where the peak should be
SLOPE1: minimum slope to the left of peak
SLOPE2: minimum slope to the right of peak
MIN_SIZE: minimum size of peak
TARGET_WIDTH = 3: peak must be within this many bins to the left/right
