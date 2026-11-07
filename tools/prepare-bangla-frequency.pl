#!/usr/bin/env perl
# Turns a Bangla word-frequency list into the compact file the suggestion engine loads.
#
# Usage:
#   perl tools/prepare-bangla-frequency.pl <bn_50k.txt> <out resources dir>
#
# Input: FrequencyWords (https://github.com/hermitdave/FrequencyWords), content/2018/bn/bn_50k.txt
#   ("word count" per line, counted from OpenSubtitles 2018). Code MIT; the word counts are
#   derived from OpenSubtitles and shared under CC BY-SA 4.0 (see bn/NOTICE.txt).
#
# Output: <out>/bn/bangla-frequency.tsv   word<TAB>weight(1-255)   sorted by code point
#   - only words made of Bangla letters and signs (no digits, punctuation or Latin)
#   - য় ড় ঢ় written as one character (U+09DF/09DC/09DD), as everywhere else in Jolpai
#   - counts of words that become the same after that are added together
#   - weight = 12 × log2(count + 1), so 1 occurrence ≈ 12 and the most common word ≈ 207
#   - words seen fewer than 3 times are dropped (mostly typos and names)
use strict;
use warnings;
use utf8;
use open qw(:std :encoding(UTF-8));
use File::Path qw(make_path);

my ($in, $out) = @ARGV;
die "usage: $0 <bn_50k.txt> <out dir>\n" unless $out;
make_path("$out/bn");

my %count;
open my $fh, '<', $in or die "$in: $!";
while (my $line = <$fh>) {
    chomp $line;
    $line =~ s/\r$//;
    my ($word, $n) = $line =~ /^(\S+)\s+(\d+)$/ or next;
    $word =~ s/\x{09AF}\x{09BC}/\x{09DF}/g;   # য + ়  → য়
    $word =~ s/\x{09A1}\x{09BC}/\x{09DC}/g;   # ড + ়  → ড়
    $word =~ s/\x{09A2}\x{09BC}/\x{09DD}/g;   # ঢ + ়  → ঢ়
    next unless $word =~ /^[\x{0980}-\x{09FF}\x{200C}\x{200D}]+$/;
    next if $word =~ /[\x{09E6}-\x{09EF}]/;   # Bangla digits
    next if $word =~ /^[\x{0964}\x{0965}]+$/;
    next if length($word) < 2;
    $count{$word} += $n;
}
close $fh;

my $kept = 0;
open my $o, '>', "$out/bn/bangla-frequency.tsv" or die $!;
for my $w (sort keys %count) {
    my $n = $count{$w};
    next if $n < 3;
    my $weight = int(12 * log($n + 1) / log(2));
    $weight = 1 if $weight < 1;
    $weight = 255 if $weight > 255;
    print $o "$w\t$weight\n";
    $kept++;
}
close $o;
print "bn/bangla-frequency.tsv: $kept words\n";
