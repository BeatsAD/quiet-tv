package main

/*
#include <stdlib.h>
*/
import "C"
import "unsafe"

//export qt_remote
func qt_remote(raw *C.char) *C.char { return C.CString(invoke(C.GoString(raw))) }

//export qt_remote_free
func qt_remote_free(p *C.char) { C.free(unsafe.Pointer(p)) }
