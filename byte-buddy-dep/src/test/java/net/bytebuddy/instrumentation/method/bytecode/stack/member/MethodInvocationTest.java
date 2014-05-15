package net.bytebuddy.instrumentation.method.bytecode.stack.member;

import net.bytebuddy.instrumentation.Instrumentation;
import net.bytebuddy.instrumentation.method.MethodDescription;
import net.bytebuddy.instrumentation.method.bytecode.stack.StackManipulation;
import net.bytebuddy.instrumentation.method.bytecode.stack.StackSize;
import net.bytebuddy.instrumentation.type.TypeDescription;
import net.bytebuddy.utility.MockitoRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Arrays;
import java.util.Collection;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;

@RunWith(Parameterized.class)
public class MethodInvocationTest {

    private static final String FOO = "foo", BAR = "bar", QUX = "qux", BAZ = "baz";
    private static final int ARGUMENT_STACK_SIZE = 1;
    private final StackSize stackSize;
    private final int expectedSize;
    @Rule
    public TestRule mockitoRule = new MockitoRule(this);
    @Mock
    private MethodDescription methodDescription;
    @Mock
    private TypeDescription returnType, declaringType, otherType;
    @Mock
    private Instrumentation.Context instrumentationContext;
    @Mock
    private MethodVisitor methodVisitor;

    public MethodInvocationTest(StackSize stackSize) {
        this.stackSize = stackSize;
        this.expectedSize = stackSize.getSize() - ARGUMENT_STACK_SIZE;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {StackSize.ZERO},
                {StackSize.SINGLE},
                {StackSize.DOUBLE}
        });
    }

    @Before
    public void setUp() throws Exception {
        when(methodDescription.getReturnType()).thenReturn(returnType);
        when(methodDescription.getDeclaringType()).thenReturn(declaringType);
        when(methodDescription.getStackSize()).thenReturn(ARGUMENT_STACK_SIZE);
        when(declaringType.getInternalName()).thenReturn(FOO);
        when(otherType.getInternalName()).thenReturn(BAZ);
        when(methodDescription.getInternalName()).thenReturn(BAR);
        when(methodDescription.getDescriptor()).thenReturn(QUX);
        when(returnType.getStackSize()).thenReturn(stackSize);
    }

    @After
    public void tearDown() throws Exception {
        verifyZeroInteractions(instrumentationContext);
    }

    @Test
    public void testStaticMethodInvocation() throws Exception {
        when(methodDescription.isStatic()).thenReturn(true);
        assertInvocation(MethodInvocation.invoke(methodDescription), Opcodes.INVOKESTATIC, FOO);
    }

    @Test
    public void testStaticPrivateMethodInvocation() throws Exception {
        when(methodDescription.isStatic()).thenReturn(true);
        when(methodDescription.isPrivate()).thenReturn(true);
        assertInvocation(MethodInvocation.invoke(methodDescription), Opcodes.INVOKESTATIC, FOO);
    }

    @Test
    public void testPrivateMethodInvocation() throws Exception {
        when(methodDescription.isPrivate()).thenReturn(true);
        assertInvocation(MethodInvocation.invoke(methodDescription), Opcodes.INVOKESPECIAL, FOO);
    }

    @Test
    public void testConstructorMethodInvocation() throws Exception {
        when(methodDescription.isConstructor()).thenReturn(true);
        assertInvocation(MethodInvocation.invoke(methodDescription), Opcodes.INVOKESPECIAL, FOO);
    }

    @Test
    public void testPublicMethodInvocation() throws Exception {
        assertInvocation(MethodInvocation.invoke(methodDescription), Opcodes.INVOKEVIRTUAL, FOO);
    }

    @Test
    public void testInterfaceMethodInvocation() throws Exception {
        when(declaringType.isInterface()).thenReturn(true);
        assertInvocation(MethodInvocation.invoke(methodDescription), Opcodes.INVOKEINTERFACE, FOO);
    }

    @Test
    public void testDefaultInterfaceMethodInvocation() throws Exception {
        when(methodDescription.isDefaultMethod()).thenReturn(true);
        when(declaringType.isInterface()).thenReturn(true);
        assertInvocation(MethodInvocation.invoke(methodDescription), Opcodes.INVOKESPECIAL, FOO);
    }

    @Test
    public void testExplicitlySpecialMethodInvocation() throws Exception {
        when(methodDescription.isSpecializableFor(otherType)).thenReturn(true);
        assertInvocation(MethodInvocation.invoke(methodDescription).special(otherType), Opcodes.INVOKESPECIAL, BAZ);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalSpecialMethodInvocation() throws Exception {
        MethodInvocation.invoke(methodDescription).special(otherType);
    }

    @Test
    public void testExplicitlyVirtualMethodInvocation() throws Exception {
        when(declaringType.isAssignableFrom(otherType)).thenReturn(true);
        assertInvocation(MethodInvocation.invoke(methodDescription).virtual(otherType), Opcodes.INVOKEVIRTUAL, BAZ);
    }

    @Test
    public void testExplicitlyVirtualMethodInvocationOfInterface() throws Exception {
        when(declaringType.isAssignableFrom(otherType)).thenReturn(true);
        when(otherType.isInterface()).thenReturn(true);
        assertInvocation(MethodInvocation.invoke(methodDescription).virtual(otherType), Opcodes.INVOKEINTERFACE, BAZ);
    }

    @Test(expected = IllegalStateException.class)
    public void testStaticVirtualInvocation() throws Exception {
        when(methodDescription.isStatic()).thenReturn(true);
        MethodInvocation.invoke(methodDescription).virtual(otherType);
    }

    @Test(expected = IllegalStateException.class)
    public void testPrivateVirtualInvocation() throws Exception {
        when(methodDescription.isPrivate()).thenReturn(true);
        MethodInvocation.invoke(methodDescription).virtual(otherType);
    }

    @Test(expected = IllegalStateException.class)
    public void testConstructorVirtualInvocation() throws Exception {
        when(methodDescription.isConstructor()).thenReturn(true);
        MethodInvocation.invoke(methodDescription).virtual(otherType);
    }

    private void assertInvocation(StackManipulation stackManipulation, int opcode, String typeName) {
        assertThat(stackManipulation.isValid(), is(true));
        StackManipulation.Size size = stackManipulation.apply(methodVisitor, instrumentationContext);
        assertThat(size.getSizeImpact(), is(expectedSize));
        assertThat(size.getMaximalSize(), is(Math.max(0, expectedSize)));
        verify(methodVisitor).visitMethodInsn(opcode, typeName, BAR, QUX);
        verifyNoMoreInteractions(methodVisitor);
    }
}