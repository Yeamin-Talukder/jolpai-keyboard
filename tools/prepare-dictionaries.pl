#!/usr/bin/env perl
# Converts the upstream word data into the compact files the suggestion engine loads.
#
# Usage:
#   perl tools/prepare-dictionaries.pl <aosp en_US_wordlist.combined> <upodesh data dir> <out resources dir>
#
# Inputs (download them first; see docs/ROADMAP.md, Phase 3, for sources and commits):
#   - AOSP LatinIME dictionaries/en_US_wordlist.combined (Apache-2.0), gunzipped
#   - upodesh data/source-words.txt, data/preprocessed-patterns.json,
#     data/source-common-patterns.txt (MIT)
#
# Outputs (in <out>):
#   en/en_US_words.tsv      word<TAB>frequency(0-255)<TAB>flags   sorted by lower-cased word
#                           flags: "o" = possibly offensive (never suggested unless typed in full)
#   en/en_US_shortcuts.tsv  typed<TAB>replacement                 e.g. "aint" -> "ain't"
#   bn/bangla-words.txt     one word per line, unique, sorted by code point
#   bn/avro-patterns.tsv    roman<TAB>optional(0|1)<TAB>bangla1|bangla2|...
#   bn/common-suffix-patterns.txt  copied as is
use strict;
use warnings;
use utf8;
use open qw(:std :encoding(UTF-8));
use JSON::PP;
use File::Path qw(make_path);

my ($aosp, $upodesh, $out) = @ARGV;
die "usage: $0 <en_US_wordlist.combined> <upodesh data dir> <out dir>\n" unless $out;
make_path("$out/en", "$out/bn");

# ---------------------------------------------------------------- English (AOSP)
my (@words, @shortcuts);
my $last_not_a_word;
open my $in, '<', $aosp or die "$aosp: $!";
while (my $line = <$in>) {
    chomp $line;
    if ($line =~ /^ word=([^,]+),f=(\d+)(.*)$/) {
        my ($word, $freq, $rest) = ($1, $2, $3);
        if ($rest =~ /not_a_word=true/) {
            $last_not_a_word = $word;
            next;
        }
        $last_not_a_word = undef;
        die "tab in word '$word'" if $word =~ /\t/;
        my $flags = $rest =~ /possibly_offensive=true/ ? 'o' : '';
        push @words, [$word, $freq, $flags];
    } elsif ($line =~ /^  shortcut=([^,]+),f=whitelist/ && defined $last_not_a_word) {
        push @shortcuts, [lc $last_not_a_word, $1];
    }
}
close $in;
@words = sort { lc($a->[0]) cmp lc($b->[0]) or $b->[1] <=> $a->[1] or $a->[0] cmp $b->[0] } @words;
open my $ew, '>', "$out/en/en_US_words.tsv" or die $!;
print $ew join("\t", @$_), "\n" for @words;
close $ew;
open my $es, '>', "$out/en/en_US_shortcuts.tsv" or die $!;
print $es "$_->[0]\t$_->[1]\n" for sort { $a->[0] cmp $b->[0] } @shortcuts;
close $es;
printf "English: %d words, %d shortcuts\n", scalar @words, scalar @shortcuts;

# ---------------------------------------------------------------- Bangla (upodesh)
my %seen;
open my $bw, '<', "$upodesh/data_source-words.txt" or die $!;
while (my $w = <$bw>) {
    $w =~ s/^\s+|\s+$//g;
    $seen{$w} = 1 if length $w;
}
close $bw;
my @bangla = sort keys %seen;
open my $bo, '>', "$out/bn/bangla-words.txt" or die $!;
print $bo "$_\n" for @bangla;
close $bo;
printf "Bangla: %d words\n", scalar @bangla;

local $/;
open my $pj, '<:raw', "$upodesh/data_preprocessed-patterns.json" or die $!;
my $patterns = JSON::PP->new->utf8->decode(<$pj>);
close $pj;
open my $po, '>', "$out/bn/avro-patterns.tsv" or die $!;
for my $key (sort keys %$patterns) {
    my $block = $patterns->{$key};
    my @t = @{ $block->{transliterate} };
    for (@t, $key) { die "separator inside pattern data: '$_'" if /[\t|\n]/ }
    my $optional = ($block->{entireBlockOptional} && $block->{entireBlockOptional} ne 'false') ? 1 : 0;
    print $po join("\t", $key, $optional, join('|', @t)), "\n";
}
close $po;
printf "Avro patterns: %d\n", scalar keys %$patterns;

open my $cs, '<', "$upodesh/data_source-common-patterns.txt" or die $!;
my $common = <$cs>;
close $cs;
open my $co, '>', "$out/bn/common-suffix-patterns.txt" or die $!;
print $co $common;
close $co;
