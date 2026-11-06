#!/usr/bin/env perl
# Converts Unicode emoji data and CLDR annotations into the files emoji-engine loads.
#
# Usage:
#   perl tools/prepare-emoji.pl <emoji-test.txt> <cldr dir with annotations-{en,bn}.xml and annotationsDerived-{en,bn}.xml> <out dir>
#
# Outputs:
#   emoji.tsv          emoji<TAB>group<TAB>name<TAB>tone1|tone2|tone3|tone4|tone5   (tones empty if none)
#                      base emoji only, in Unicode's recommended order; "Component" group skipped
#   keywords-en.tsv    emoji<TAB>name<TAB>keyword|keyword|...
#   keywords-bn.tsv    same, Bangla
use strict;
use warnings;
use utf8;
use open qw(:std :encoding(UTF-8));
use File::Path qw(make_path);

my ($test, $cldr, $out) = @ARGV;
die "usage: $0 <emoji-test.txt> <cldr dir> <out dir>\n" unless $out;
make_path($out);

my $MOD = qr/[\x{1F3FB}-\x{1F3FF}]/;
sub key_of { my $s = shift; $s =~ s/\x{FE0F}//g; $s =~ s/$MOD//g; return $s; }

my (@base, %base_index, %tones, $group);
open my $in, '<', $test or die "$test: $!";
while (my $line = <$in>) {
    if ($line =~ /^# group: (.+?)\s*$/) { $group = $1; next; }
    next unless $line =~ /^([0-9A-F ]+?)\s*;\s*fully-qualified\s*#\s*(\S+)\s+E\d+\.\d+\s+(.+?)\s*$/;
    next if $group eq 'Component';
    my ($cps, $emoji, $name) = ($1, $2, $3);
    my @mods = $emoji =~ /($MOD)/g;
    if (@mods) {
        my %distinct = map { $_ => 1 } @mods;
        next if keys %distinct > 1;              # mixed tones (couples): offer single tones only
        my $tone = ord($mods[0]) - 0x1F3FB;      # 0..4
        $tones{key_of($emoji)}[$tone] //= $emoji;
        next;
    }
    $base_index{key_of($emoji)} = scalar @base;
    push @base, [$emoji, $group, $name];
}
close $in;

open my $eo, '>', "$out/emoji.tsv" or die $!;
for my $b (@base) {
    my $t = $tones{key_of($b->[0])};
    my $tones = ($t && 5 == grep { defined } @$t) ? join('|', @$t) : '';
    print $eo join("\t", $b->[0], $b->[1], $b->[2], $tones), "\n";
}
close $eo;
printf "emoji: %d base, %d with skin tones\n", scalar @base, scalar grep { $_ && 5 == grep { defined } @$_ } values %tones;

sub unescape {
    my $s = shift;
    $s =~ s/&lt;/</g; $s =~ s/&gt;/>/g; $s =~ s/&quot;/"/g; $s =~ s/&apos;/'/g; $s =~ s/&amp;/&/g;
    return $s;
}

for my $lang (qw(en bn)) {
    my (%names, %words);
    for my $file ("$cldr/annotations-$lang.xml", "$cldr/annotationsDerived-$lang.xml") {
        open my $x, '<', $file or die "$file: $!";
        while (my $line = <$x>) {
            next unless $line =~ /<annotation cp="([^"]+)"( type="tts")?>([^<]*)<\/annotation>/;
            my ($cp, $tts, $text) = (unescape($1), $2, unescape($3));
            next if $cp =~ $MOD;
            my $k = key_of($cp);
            next unless exists $base_index{$k};
            if ($tts) { $names{$k} //= $text; }
            else { push @{ $words{$k} }, map { s/^\s+|\s+$//gr } split /\|/, $text; }
        }
        close $x;
    }
    open my $ko, '>', "$out/keywords-$lang.tsv" or die $!;
    my $count = 0;
    for my $b (@base) {
        my $k = key_of($b->[0]);
        next unless $names{$k} || $words{$k};
        my %seen;
        my @w = grep { length && !$seen{$_}++ } @{ $words{$k} || [] };
        print $ko join("\t", $b->[0], $names{$k} // '', join('|', @w)), "\n";
        $count++;
    }
    close $ko;
    print "keywords-$lang: $count emoji\n";
}
