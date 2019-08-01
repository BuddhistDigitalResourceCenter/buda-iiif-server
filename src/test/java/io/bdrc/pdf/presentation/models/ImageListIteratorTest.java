package io.bdrc.pdf.presentation.models;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.junit.Test;

import io.bdrc.libraries.ImageListIterator;

public class ImageListIteratorTest {

    public void assertIterator(String test, List<String> expected, int beginImageSeqNum, int endImageSeqNum) {
        Iterator<String> it = new ImageListIterator(test, beginImageSeqNum, endImageSeqNum);
        List<String> res = new ArrayList<>();
        it.forEachRemaining(res::add);
        assertThat(res, is(expected));
    }

    public void assertIterator(String test, List<String> expected) {
        assertIterator(test, expected, 1, -1);
    }

    @Test
    public void testIteratorSimple() {
        assertIterator("abc.tf|ghi.jp", Arrays.asList("abc.tf", "ghi.jp"));
        assertIterator("abc.tf|ghi.jp", Arrays.asList("abc.tf"), 1, 1);
        assertIterator("abc.tf|ghi.jp", Arrays.asList("ghi.jp"), 2, 2);
        assertIterator("abc.tf|ghi.jp|test.3", Arrays.asList("abc.tf", "ghi.jp"), 1, 2);
    }

    @Test
    public void testIteratorSeq() {
        assertIterator("abc0001.tf:1|ghi0001.jp:1", Arrays.asList("abc0001.tf", "ghi0001.jp"));
        assertIterator("abc0001.tf:2|ghi0001.jp:1", Arrays.asList("abc0001.tf", "abc0002.tf", "ghi0001.jp"));
        assertIterator("abc0001.tf:2", Arrays.asList("abc0001.tf", "abc0002.tf"));
        assertIterator("abc0001.tf:2|ghi0001.jp:1", Arrays.asList("abc0002.tf"), 2, 2);
        assertIterator("abc0001.tf:2|ghi0001.jp:1", Arrays.asList("ghi0001.jp"), 3, 3);
        assertIterator("abc0001.tf:20|ghi0021.jp:30", Arrays.asList("abc0019.tf", "abc0020.tf", "ghi0021.jp", "ghi0022.jp"), 19, 22);
        assertIterator("abc0001.tf:20|ijk.tt|ghi0021.jp:30", Arrays.asList("abc0020.tf", "ijk.tt", "ghi0021.jp", "ghi0022.jp"), 20, 23);
        assertIterator("abc0001.tf:2|ghi0001.jp:1", Arrays.asList("ghi0001.jp"), 3, -1);
    }

}
